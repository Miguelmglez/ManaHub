package com.mmg.manahub.feature.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.model.BuilderStep
import com.mmg.manahub.core.domain.model.BuilderTab
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckBuilderState
import com.mmg.manahub.core.domain.model.DeckCard
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.ReviewGroupBy
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.domain.usecase.decks.DeckCardValidator
import com.mmg.manahub.feature.decks.engine.DeckImportExportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckBuilderViewModel @Inject constructor(
    private val userCardRepository: UserCardRepository,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DeckBuilderState())
    val state: StateFlow<DeckBuilderState> = _state.asStateFlow()

    val preferredCurrency: StateFlow<PreferredCurrency> = userPreferencesRepository.preferredCurrencyFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PreferredCurrency.EUR
        )

    // Commander search state (separate from main state)
    private val _commanderResults = MutableStateFlow<List<Card>>(emptyList())
    val commanderResults: StateFlow<List<Card>> = _commanderResults.asStateFlow()

    private val _isSearchingCommander = MutableStateFlow(false)
    val isSearchingCommander: StateFlow<Boolean> = _isSearchingCommander.asStateFlow()

    // ── Setup ─────────────────────────────────────────────────────────────────

    fun setupDeck(name: String, format: DeckFormat, commander: Card? = null) {
        val colorIdentity = commander?.colorIdentity
            ?.map { it.uppercase() }?.toSet() ?: emptySet()

        _state.update {
            it.copy(
                deckName = name,
                format = format,
                commander = commander,
                commanderColorIdentity = colorIdentity,
                step = BuilderStep.BUILDING,
            )
        }
        loadCollectionCards()
        loadScryfallSuggestions()
    }

    // ── Collection loading ────────────────────────────────────────────────────

    private fun loadCollectionCards() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingCollection = true) }
            try {
                val allOwned = userCardRepository.observeCollection().first()
                val filtered = allOwned
                    .filter { uwc ->
                        !BasicLandCalculator.isBasicLand(uwc.card) &&
                            isCardAllowedInDeck(uwc.card)
                    }
                    .map { uwc ->
                        DeckCard(
                            card = uwc.card,
                            quantity = uwc.userCard.quantity,
                            isOwned = true,
                        )
                    }
                    .sortedBy { it.card.name }

                _state.update { it.copy(collectionCards = filtered, isLoadingCollection = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingCollection = false, error = e.message) }
            }
        }
    }

    private fun loadScryfallSuggestions() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingSuggestions = true) }
            try {
                val s = _state.value
                val prefs = userPreferencesRepository.preferencesFlow.first()
                val query = buildSuggestionQuery(s, prefs.cardLanguage)
                val results = cardRepository.searchWithRawQuery(query)
                val ownedIds = s.collectionCards.map { it.card.scryfallId }.toSet()
                val suggestions = results
                    .filter { card ->
                        !BasicLandCalculator.isBasicLand(card) && isCardAllowedInDeck(card)
                    }
                    .map { card ->
                        DeckCard(
                            card = card,
                            quantity = 1,
                            isOwned = card.scryfallId in ownedIds,
                        )
                    }

                _state.update { it.copy(suggestions = suggestions, isLoadingSuggestions = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingSuggestions = false) }
            }
        }
    }

    private fun buildSuggestionQuery(s: DeckBuilderState, lang: CardLanguage): String = buildString {
        append("f:${s.format.name.lowercase()}")
        if (s.format.requiresCommander && s.commanderColorIdentity.isNotEmpty()) {
            val colors = s.commanderColorIdentity.joinToString("") { it.lowercase() }
            append(" id:$colors")
        }
        append(" -t:land")
        // Filter by the user's preferred card language
        append(" lang:${lang.code.split("-")[0]}")
        append(" order:edhrec")
    }

    private fun isCardAllowedInDeck(card: Card): Boolean {
        val s = _state.value
        if (s.format.requiresCommander && s.commanderColorIdentity.isNotEmpty()) {
            val cardIdentity = card.colorIdentity.map { it.uppercase() }.toSet()
            if (!s.commanderColorIdentity.containsAll(cardIdentity)) return false
        }
        return true
    }

    // ── Commander search ──────────────────────────────────────────────────────

    fun searchCommander(query: String) {
        if (query.length < 2) { _commanderResults.value = emptyList(); return }
        viewModelScope.launch {
            _isSearchingCommander.value = true
            try {
                val results = cardRepository.searchWithRawQuery(
                    "t:legendary t:creature $query"
                )
                _commanderResults.value = results
            } catch (e: Exception) {
                _commanderResults.value = emptyList()
            }
            _isSearchingCommander.value = false
        }
    }

    fun clearCommanderSearch() {
        _commanderResults.value = emptyList()
    }

    // ── Mainboard actions ─────────────────────────────────────────────────────

    fun addToMainboard(deckCard: DeckCard) {
        val s = _state.value
        val card = deckCard.card

        // Non-basic lands go to their own section
        if (BasicLandCalculator.isLand(card) && !BasicLandCalculator.isBasicLand(card)) {
            addNonBasicLand(deckCard)
            return
        }

        val existing = s.mainboard.find { it.card.scryfallId == card.scryfallId }
        val currentQty = existing?.quantity ?: 0
        val addResult = DeckCardValidator.canAddCard(card, s.format, currentQty)
        if (addResult == DeckCardValidator.AddResult.BLOCKED) return

        val newMainboard = if (existing != null) {
            s.mainboard.map { dc ->
                if (dc.card.scryfallId == card.scryfallId)
                    dc.copy(quantity = dc.quantity + 1)
                else dc
            }
        } else {
            s.mainboard + deckCard.copy(quantity = 1)
        }

        _state.update { it.copy(mainboard = newMainboard) }
        recalculateBasicLands()
    }

    fun acknowledgeOverLimit(scryfallId: String) {
        _state.update {
            it.copy(acknowledgedOverLimitCards = it.acknowledgedOverLimitCards + scryfallId)
        }
    }

    fun unacknowledgeOverLimit(scryfallId: String) {
        _state.update {
            it.copy(acknowledgedOverLimitCards = it.acknowledgedOverLimitCards - scryfallId)
        }
    }

    fun addToSideboard(deckCard: DeckCard) {
        val s = _state.value
        val existing = s.sideboard.find { it.card.scryfallId == deckCard.card.scryfallId }
        val newSideboard = if (existing != null) {
            s.sideboard.map { dc ->
                if (dc.card.scryfallId == deckCard.card.scryfallId)
                    dc.copy(quantity = dc.quantity + 1)
                else dc
            }
        } else {
            s.sideboard + deckCard.copy(quantity = 1)
        }
        _state.update { it.copy(sideboard = newSideboard) }
    }

    fun removeFromMainboard(scryfallId: String) {
        val newMainboard = _state.value.mainboard.mapNotNull { dc ->
            if (dc.card.scryfallId == scryfallId) {
                if (dc.quantity > 1) dc.copy(quantity = dc.quantity - 1) else null
            } else dc
        }
        _state.update { it.copy(mainboard = newMainboard) }
        recalculateBasicLands()
    }

    fun removeFromSideboard(scryfallId: String) {
        val newSideboard = _state.value.sideboard.mapNotNull { dc ->
            if (dc.card.scryfallId == scryfallId) {
                if (dc.quantity > 1) dc.copy(quantity = dc.quantity - 1) else null
            } else dc
        }
        _state.update { it.copy(sideboard = newSideboard) }
    }

    fun moveToSideboard(scryfallId: String) {
        val card = _state.value.mainboard.find { it.card.scryfallId == scryfallId } ?: return
        removeFromMainboard(scryfallId)
        addToSideboard(card)
    }

    fun moveToMainboard(scryfallId: String) {
        val card = _state.value.sideboard.find { it.card.scryfallId == scryfallId } ?: return
        removeFromSideboard(scryfallId)
        addToMainboard(card)
    }

    private fun addNonBasicLand(deckCard: DeckCard) {
        val existing = _state.value.nonBasicLands.find { it.card.scryfallId == deckCard.card.scryfallId }
        val newNonBasic = if (existing != null) {
            _state.value.nonBasicLands.map { dc ->
                if (dc.card.scryfallId == deckCard.card.scryfallId)
                    dc.copy(quantity = dc.quantity + 1)
                else dc
            }
        } else {
            _state.value.nonBasicLands + deckCard.copy(quantity = 1)
        }
        _state.update { it.copy(nonBasicLands = newNonBasic) }
        recalculateBasicLands()
    }

    fun removeNonBasicLand(scryfallId: String) {
        val newNonBasic = _state.value.nonBasicLands.mapNotNull { dc ->
            if (dc.card.scryfallId == scryfallId) {
                if (dc.quantity > 1) dc.copy(quantity = dc.quantity - 1) else null
            } else dc
        }
        _state.update { it.copy(nonBasicLands = newNonBasic) }
        recalculateBasicLands()
    }

    private fun recalculateBasicLands() {
        val s = _state.value
        val distribution = BasicLandCalculator.calculate(
            mainboard = s.mainboard,
            nonBasicLands = s.nonBasicLands,
            format = s.format,
        )
        _state.update { it.copy(basicLands = distribution) }
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    fun toggleColorFilter(color: String) {
        val current = _state.value.filterColors.toMutableSet()
        if (current.contains(color)) current.remove(color) else current.add(color)
        _state.update { it.copy(filterColors = current) }
    }

    fun setTypeFilter(type: String) {
        _state.update { it.copy(filterType = type) }
    }

    fun setMaxCmcFilter(cmc: Int?) {
        _state.update { it.copy(filterMaxCmc = cmc) }
    }

    fun setMaxPriceFilter(price: Double?) {
        _state.update { it.copy(filterMaxPrice = price) }
    }

    fun clearFilters() {
        _state.update { it.copy(filterColors = emptySet(), filterType = "", filterMaxCmc = null, filterMaxPrice = null) }
    }

    fun setActiveTab(tab: BuilderTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    fun setReviewGroupBy(groupBy: ReviewGroupBy) {
        _state.update { it.copy(reviewGroupBy = groupBy) }
    }

    fun goToReview() {
        _state.update { it.copy(step = BuilderStep.REVIEW) }
    }

    fun goBackToBuilding() {
        _state.update { it.copy(step = BuilderStep.BUILDING) }
    }

    fun getFilteredCards(cards: List<DeckCard>, currency: PreferredCurrency): List<DeckCard> {
        val s = _state.value
        return cards.filter { dc ->
            val card = dc.card
            val matchesColor = s.filterColors.isEmpty() ||
                s.filterColors.any { c -> card.colors.any { it.equals(c, true) } }
            val matchesType = s.filterType.isBlank() ||
                card.typeLine.contains(s.filterType, ignoreCase = true)
            val matchesCmc = s.filterMaxCmc == null || card.cmc.toInt() <= s.filterMaxCmc!!

            val cardPrice = if (currency == PreferredCurrency.EUR) {
                card.priceEur ?: card.priceEurFoil
            } else {
                card.priceUsd ?: card.priceUsdFoil
            }
            val matchesPrice = s.filterMaxPrice == null || (cardPrice != null && cardPrice <= s.filterMaxPrice)

            matchesColor && matchesType && matchesCmc && matchesPrice
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Parses [text] in Moxfield/Arena format, resolves every card name via Scryfall,
     * and creates a new deck — bypassing the manual builder steps.
     */
    fun importDeckFromText(
        text:      String,
        deckName:  String,
        format:    DeckFormat,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(error = null, isLoadingCollection = true) }
            try {
                val parsed = DeckImportExportHelper.parse(text)

                // Resolve commander
                val commanderCard: Card? = parsed.commander?.let { line ->
                    cardRepository.getCardByExactName(line.name).getOrNull()
                }

                // Resolve mainboard cards
                val mainboard = parsed.mainboard.mapNotNull { line ->
                    val card = cardRepository.getCardByExactName(line.name).getOrNull()
                    card?.let { DeckCard(card = it, quantity = line.quantity) }
                }

                // Resolve sideboard cards
                val sideboard = parsed.sideboard.mapNotNull { line ->
                    val card = cardRepository.getCardByExactName(line.name).getOrNull()
                    card?.let { DeckCard(card = it, quantity = line.quantity) }
                }

                val deckId = deckRepository.createDeck(
                    name        = deckName.ifBlank { parsed.commander?.name ?: "Imported Deck" },
                    description = "",
                    format      = format.name.lowercase(),
                )

                commanderCard?.let {
                    deckRepository.addCardToDeck(deckId, it.scryfallId, 1, false)
                }
                mainboard.forEach { dc ->
                    deckRepository.addCardToDeck(deckId, dc.card.scryfallId, dc.quantity, false)
                }
                sideboard.forEach { dc ->
                    deckRepository.addCardToDeck(deckId, dc.card.scryfallId, dc.quantity, true)
                }

                _state.update { it.copy(isLoadingCollection = false) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingCollection = false, error = e.message) }
            }
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveDeck(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val basicLandCards = try {
                buildBasicLandDeckCards(s)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
                return@launch
            }

            // Create the deck row first, then add all cards.
            // If any card insertion fails we delete the orphaned deck row so we
            // don't leave a nameless, empty deck in the list.
            val deckId = try {
                deckRepository.createDeck(
                    name        = s.deckName,
                    description = "",
                    format      = s.format.name.lowercase(),
                )
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
                return@launch
            }

            try {
                s.commander?.let {
                    deckRepository.addCardToDeck(deckId, it.scryfallId, 1, false)
                }
                (s.mainboard + s.nonBasicLands + basicLandCards).forEach { dc ->
                    deckRepository.addCardToDeck(deckId, dc.card.scryfallId, dc.quantity, false)
                }
                s.sideboard.forEach { dc ->
                    deckRepository.addCardToDeck(deckId, dc.card.scryfallId, dc.quantity, true)
                }
                onSuccess()
            } catch (e: Exception) {
                // Compensating delete: remove the orphaned deck row so the user
                // does not see an empty, broken deck in their list.
                runCatching { deckRepository.deleteDeck(deckId) }
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private suspend fun buildBasicLandDeckCards(s: DeckBuilderState): List<DeckCard> {
        val landNames = mapOf(
            "W" to "Plains", "U" to "Island", "B" to "Swamp",
            "R" to "Mountain", "G" to "Forest",
        )
        val result = mutableListOf<DeckCard>()
        s.basicLands.toMap().forEach { (color, count) ->
            if (count > 0) {
                val landName = landNames[color] ?: return@forEach
                try {
                    val land = cardRepository.getCardByExactName(landName).getOrThrow()
                    result.add(DeckCard(card = land, quantity = count))
                } catch (_: Exception) {
                    // Skip if lookup fails — land names are still saved as scryfallId in basic form
                }
            }
        }
        return result
    }
}
