package com.mmg.magicfolder.feature.addcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.data.remote.ScryfallRemoteDataSource
import com.mmg.magicfolder.core.domain.model.AdvancedSearchQuery
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.ComparisonOperator
import com.mmg.magicfolder.core.domain.model.MagicSet
import com.mmg.magicfolder.core.domain.model.SearchCriterion
import com.mmg.magicfolder.core.domain.model.SearchDirection
import com.mmg.magicfolder.core.domain.model.SearchOrder
import com.mmg.magicfolder.core.domain.usecase.search.BuildScryfallQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdvancedSearchViewModel @Inject constructor(
    private val scryfallDataSource: ScryfallRemoteDataSource,
    private val buildQuery: BuildScryfallQueryUseCase,
) : ViewModel() {

    data class UiState(
        val nameValue: String = "",
        val nameExact: Boolean = false,
        val oracleText: String = "",
        val cardType: String = "",
        val selectedColors: Set<String> = emptySet(),
        val colorsExact: Boolean = false,
        val useColorIdentity: Boolean = false,
        val manaCostValue: String = "",
        val manaCostOp: ComparisonOperator = ComparisonOperator.EQUAL,
        val selectedRarity: String = "",
        val rarityOp: ComparisonOperator = ComparisonOperator.EQUAL,
        val selectedSet: MagicSet? = null,
        val powerValue: String = "",
        val powerOp: ComparisonOperator = ComparisonOperator.EQUAL,
        val toughnessValue: String = "",
        val toughnessOp: ComparisonOperator = ComparisonOperator.EQUAL,
        val priceMax: String = "",
        val priceCurrency: String = "eur",
        val selectedFormat: String = "",
        val formatLegal: Boolean = true,
        val keyword: String = "",
        val orderBy: SearchOrder = SearchOrder.NAME,
        val orderDirection: SearchDirection = SearchDirection.ASC,
        val builtQuery: String = "",
        val currentQuery: AdvancedSearchQuery = AdvancedSearchQuery(),
        val results: List<Card> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val hasSearched: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private fun rebuildQuery(): AdvancedSearchQuery {
        val s = _uiState.value
        val criteria = mutableListOf<SearchCriterion>()

        if (s.nameValue.isNotBlank())
            criteria.add(SearchCriterion.Name(s.nameValue, s.nameExact))
        if (s.oracleText.isNotBlank())
            criteria.add(SearchCriterion.OracleText(s.oracleText))
        if (s.cardType.isNotBlank())
            criteria.add(SearchCriterion.CardType(s.cardType))
        if (s.selectedColors.isNotEmpty()) {
            if (s.useColorIdentity)
                criteria.add(SearchCriterion.ColorIdentity(s.selectedColors, s.colorsExact))
            else
                criteria.add(SearchCriterion.Colors(s.selectedColors, s.colorsExact))
        }
        s.manaCostValue.toIntOrNull()?.let {
            criteria.add(SearchCriterion.ManaCost(it, s.manaCostOp))
        }
        if (s.selectedRarity.isNotBlank())
            criteria.add(SearchCriterion.Rarity(s.selectedRarity, s.rarityOp))
        s.selectedSet?.let { set ->
            criteria.add(SearchCriterion.CardSet(set.code))
        }
        s.powerValue.toIntOrNull()?.let {
            criteria.add(SearchCriterion.Power(it, s.powerOp))
        }
        s.toughnessValue.toIntOrNull()?.let {
            criteria.add(SearchCriterion.Toughness(it, s.toughnessOp))
        }
        s.priceMax.toDoubleOrNull()?.let {
            criteria.add(SearchCriterion.Price(it, s.priceCurrency, ComparisonOperator.LESS_OR_EQUAL))
        }
        if (s.selectedFormat.isNotBlank())
            criteria.add(SearchCriterion.Format(s.selectedFormat, s.formatLegal))
        if (s.keyword.isNotBlank())
            criteria.add(SearchCriterion.Keyword(s.keyword))

        return AdvancedSearchQuery(criteria, s.orderBy, s.orderDirection)
    }

    private fun updateBuiltQuery() {
        val query = rebuildQuery()
        val queryString = buildQuery(query)
        _uiState.update { it.copy(builtQuery = queryString, currentQuery = query) }
    }

    fun setName(value: String) {
        _uiState.update { it.copy(nameValue = value) }
        updateBuiltQuery()
    }

    fun setNameExact(exact: Boolean) {
        _uiState.update { it.copy(nameExact = exact) }
        updateBuiltQuery()
    }

    fun setOracleText(value: String) {
        _uiState.update { it.copy(oracleText = value) }
        updateBuiltQuery()
    }

    fun setCardType(value: String) {
        _uiState.update { it.copy(cardType = value) }
        updateBuiltQuery()
    }

    fun toggleColor(color: String) {
        val current = _uiState.value.selectedColors.toMutableSet()
        if (current.contains(color)) current.remove(color) else current.add(color)
        _uiState.update { it.copy(selectedColors = current) }
        updateBuiltQuery()
    }

    fun setColorsExact(exact: Boolean) {
        _uiState.update { it.copy(colorsExact = exact) }
        updateBuiltQuery()
    }

    fun setUseColorIdentity(use: Boolean) {
        _uiState.update { it.copy(useColorIdentity = use) }
        updateBuiltQuery()
    }

    fun setManaCost(value: String, op: ComparisonOperator) {
        _uiState.update { it.copy(manaCostValue = value, manaCostOp = op) }
        updateBuiltQuery()
    }

    fun setRarity(rarity: String, op: ComparisonOperator) {
        _uiState.update { it.copy(selectedRarity = rarity, rarityOp = op) }
        updateBuiltQuery()
    }

    fun setSelectedSet(set: MagicSet?) {
        _uiState.update { it.copy(selectedSet = set) }
        updateBuiltQuery()
    }

    fun setPower(value: String, op: ComparisonOperator) {
        _uiState.update { it.copy(powerValue = value, powerOp = op) }
        updateBuiltQuery()
    }

    fun setToughness(value: String, op: ComparisonOperator) {
        _uiState.update { it.copy(toughnessValue = value, toughnessOp = op) }
        updateBuiltQuery()
    }

    fun setPrice(max: String, currency: String) {
        _uiState.update { it.copy(priceMax = max, priceCurrency = currency) }
        updateBuiltQuery()
    }

    fun setFormat(format: String, legal: Boolean) {
        _uiState.update { it.copy(selectedFormat = format, formatLegal = legal) }
        updateBuiltQuery()
    }

    fun setKeyword(value: String) {
        _uiState.update { it.copy(keyword = value) }
        updateBuiltQuery()
    }

    fun setOrder(order: SearchOrder, dir: SearchDirection) {
        _uiState.update { it.copy(orderBy = order, orderDirection = dir) }
        updateBuiltQuery()
    }

    fun clearAll() {
        _uiState.value = UiState()
    }

    fun search() {
        val queryString = _uiState.value.builtQuery
        if (queryString.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, hasSearched = true) }
            try {
                val results = scryfallDataSource.searchWithRawQuery(queryString)
                _uiState.update { it.copy(results = results, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Search failed") }
            }
        }
    }
}
