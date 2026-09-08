package com.mmg.manahub.core.ui.components.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CollectionSource
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.ComparisonOperator
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.SearchDirection
import com.mmg.manahub.core.model.SearchOrder
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * KMP migration — Phase 1 Hilt->Koin cutover. Resolved via `koinViewModel()` from
 * `searchWidgetsKoinModule` (see `core/ui/components/search/di/SearchKoinModule.kt`); all three
 * constructor deps are already bridged Hilt-owned singletons resolvable via `get()` (no new bridging
 * needed -- see that module's KDoc for details).
 */
class AdvancedSearchViewModel(
    private val scryfallDataSource: ScryfallRemoteDataSource,
    private val buildQuery: BuildScryfallQueryUseCase,
    private val userPreferencesDataStore: UserPreferencesDataStore,
) : ViewModel() {

    data class UiState(
        val nameValue: String = "",
        val nameExact: Boolean = false,
        val oracleText: String = "",
        val cardType: Set<String> = emptySet(),
        val cardTypeMatchAll: Boolean = true,
        // Suggestions Tab UI Polish plan (W11): backs SearchCriterion.CardType.exclude -- Deck
        // Analysis's Curve sections seed this true (e.g. "mv=3 -t:land").
        val cardTypeExclude: Boolean = false,
        val cardFunction: Set<String> = emptySet(),
        val cardFunctionMatchAll: Boolean = false,
        val selectedColors: Set<String> = emptySet(),
        /**
         * How [selectedColors] is compared, chosen by the user in the Color match picker.
         *
         * [ColorMatchMode.ANY_OF] is the default because "green or black elves" is what picking two
         * colors reads as; the previous default demanded a card carry EVERY picked color, which for
         * 2+ colors matches almost nothing. A seeded mode (Deck Analysis's Commander
         * [ColorMatchMode.AT_MOST] identity clause) is preserved verbatim by [seedFrom].
         *
         * The default is facet-dependent: on IDENTITY the default becomes
         * [ColorMatchMode.AT_MOST], because a superset test on identity is the semantic inversion
         * [SearchCriterion.ColorIdentity]'s own KDoc warns about — see [setUseColorIdentity].
         */
        val colorMode: ColorMatchMode = ColorMatchMode.ANY_OF,
        /**
         * True once the user has picked a mode in the Color match picker. While false,
         * [setUseColorIdentity] is free to swap in the facet-appropriate default; once true the
         * pick is never silently overwritten. Reset by [clearAll] and [seedFrom], which both
         * rebuild [UiState] from scratch.
         */
        val colorModeExplicit: Boolean = false,
        val useColorIdentity: Boolean = false,
        val manaCostValue: String = "",
        val manaCostOp: ComparisonOperator = ComparisonOperator.EQUAL,
        val selectedRarity: List<String> = emptyList(),
        val rarityOp: ComparisonOperator = ComparisonOperator.EQUAL,
        val selectedSets: Set<MagicSet> = emptySet(),
        val powerValue: String = "",
        val powerOp: ComparisonOperator = ComparisonOperator.EQUAL,
        val toughnessValue: String = "",
        val toughnessOp: ComparisonOperator = ComparisonOperator.EQUAL,
        val priceMax: String = "",
        val priceCurrency: String = "eur",
        val selectedFormat: List<String> = emptyList(),
        val formatLegal: Boolean = true,
        // Suggestions Tab UI Polish plan (W11): structural criteria with no free-text UiState
        // equivalent -- only ever populated via seedFrom() (Deck Analysis's "Browse for X"), never
        // hand-edited by the user through a dedicated picker (there is no realistic manual UI for
        // "curated oracle-text OR-group" or "mana production threshold"). Rendered read-only in
        // the sheet with a clear (X) action -- see the Oracle text / Mana production sections.
        val oracleTerms: SearchCriterion.OracleTerms? = null,
        val manaProduction: SearchCriterion.ManaProduction? = null,
        val orderBy: SearchOrder = SearchOrder.NAME,
        val orderDirection: SearchDirection = SearchDirection.ASC,
        val builtQuery: String = "",
        val currentQuery: AdvancedSearchQuery = AdvancedSearchQuery(),
        val results: List<Card> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val hasSearched: Boolean = false,
        // ── Collection-local filters ──────────────────────────────────────────
        /** Which of the user's lists results come from — mutually exclusive, never intersecting. */
        val collectionSource: CollectionSource = CollectionSource.COLLECTION,
        val filterTags: Set<String> = emptySet(),
    ) {
        val hasAnyCollectionFilter: Boolean
            get() = collectionSource != CollectionSource.COLLECTION || filterTags.isNotEmpty()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefCurrency = userPreferencesDataStore.preferredCurrencyFlow.first()
            _uiState.update { it.copy(priceCurrency = prefCurrency.code.lowercase()) }
            updateBuiltQuery()
        }
    }

    private fun rebuildQuery(): AdvancedSearchQuery {
        val s = _uiState.value
        val criteria = mutableListOf<SearchCriterion>()

        if (s.nameValue.isNotBlank())
            criteria.add(SearchCriterion.Name(s.nameValue, s.nameExact))
        if (s.oracleText.isNotBlank())
            criteria.add(SearchCriterion.OracleText(s.oracleText))
        if (s.cardType.isNotEmpty())
            criteria.add(SearchCriterion.CardType(s.cardType, s.cardTypeMatchAll, s.cardTypeExclude))
        if (s.cardFunction.isNotEmpty())
            criteria.add(SearchCriterion.CardFunction(s.cardFunction, s.cardFunctionMatchAll))
        if (s.selectedColors.isNotEmpty()) {
            if (s.useColorIdentity)
                criteria.add(SearchCriterion.ColorIdentity(s.selectedColors, s.colorMode))
            else
                criteria.add(SearchCriterion.Colors(s.selectedColors, s.colorMode))
        }
        s.manaCostValue.toIntOrNull()?.let {
            criteria.add(SearchCriterion.ManaCost(it, s.manaCostOp))
        }
        if (s.selectedRarity.isNotEmpty())
            criteria.add(SearchCriterion.Rarity(s.selectedRarity))
        if (s.selectedSets.isNotEmpty()) {
            criteria.add(SearchCriterion.CardSet(s.selectedSets.map { it.code }.toSet()))
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
        if (s.selectedFormat.isNotEmpty())
            criteria.add(SearchCriterion.Format(s.selectedFormat, s.formatLegal))
        s.oracleTerms?.let { criteria.add(it) }
        s.manaProduction?.let { criteria.add(it) }
        // COLLECTION is the implicit default, so it contributes no criterion (and no filter badge).
        if (s.collectionSource != CollectionSource.COLLECTION)
            criteria.add(SearchCriterion.CollectionStatus(s.collectionSource))
        if (s.filterTags.isNotEmpty())
            criteria.add(SearchCriterion.HasTag(s.filterTags.toList()))

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

    fun toggleCardType(type: String) {
        val current = _uiState.value.cardType.toMutableSet()
        if (current.contains(type)) current.remove(type) else current.add(type)
        _uiState.update { it.copy(cardType = current) }
        updateBuiltQuery()
    }

    fun setCardTypeMatchAll(matchAll: Boolean) {
        _uiState.update { it.copy(cardTypeMatchAll = matchAll) }
        updateBuiltQuery()
    }

    fun setCardTypeExclude(exclude: Boolean) {
        _uiState.update { it.copy(cardTypeExclude = exclude) }
        updateBuiltQuery()
    }

    fun clearOracleTerms() {
        _uiState.update { it.copy(oracleTerms = null) }
        updateBuiltQuery()
    }

    fun clearManaProduction() {
        _uiState.update { it.copy(manaProduction = null) }
        updateBuiltQuery()
    }

    fun toggleCardFunction(value: String) {
        val current = _uiState.value.cardFunction.toMutableSet()
        if (current.contains(value)) current.remove(value) else current.add(value)
        _uiState.update { it.copy(cardFunction = current) }
        updateBuiltQuery()
    }

    fun setCardFunctionMatchAll(matchAll: Boolean) {
        _uiState.update { it.copy(cardFunctionMatchAll = matchAll) }
        updateBuiltQuery()
    }

    fun toggleColor(color: String) {
        val current = _uiState.value.selectedColors.toMutableSet()
        if (current.contains(color)) current.remove(color) else current.add(color)
        _uiState.update { it.copy(selectedColors = current) }
        updateBuiltQuery()
    }

    fun setColorMode(mode: ColorMatchMode) {
        _uiState.update { it.copy(colorMode = mode, colorModeExplicit = true) }
        updateBuiltQuery()
    }

    /**
     * Switches the Color/Identity facet and, while the user has not picked a mode themselves,
     * carries the default that facet actually means: `id>=w or id>=u` would return Jund and 5-color
     * cards to a user asking "what fits in Azorius", so IDENTITY defaults to
     * [ColorMatchMode.AT_MOST] and COLOR back to [ColorMatchMode.ANY_OF].
     */
    fun setUseColorIdentity(use: Boolean) {
        _uiState.update {
            val mode = when {
                it.colorModeExplicit -> it.colorMode
                use -> ColorMatchMode.AT_MOST
                else -> ColorMatchMode.ANY_OF
            }
            it.copy(useColorIdentity = use, colorMode = mode)
        }
        updateBuiltQuery()
    }

    fun setManaCost(value: String, op: ComparisonOperator) {
        _uiState.update { it.copy(manaCostValue = value, manaCostOp = op) }
        updateBuiltQuery()
    }

    fun toggleSet(set: MagicSet) {
        val current = _uiState.value.selectedSets.toMutableSet()
        if (current.any { it.code == set.code }) {
            current.removeAll { it.code == set.code }
        } else {
            current.add(set)
        }
        _uiState.update { it.copy(selectedSets = current) }
        updateBuiltQuery()
    }

    fun clearSets() {
        _uiState.update { it.copy(selectedSets = emptySet()) }
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


    fun updateFormat(format: String) {
        val current = _uiState.value.selectedFormat.toMutableList()
        if (current.contains(format)) current.remove(format) else current.add(format)
        _uiState.update { it.copy(selectedFormat = current) }
        updateBuiltQuery()
    }

    fun updateLegalSwitch(legal: Boolean) {
        _uiState.update { it.copy(formatLegal = legal) }
        updateBuiltQuery()
    }

    fun setOrder(order: SearchOrder, dir: SearchDirection) {
        _uiState.update { it.copy(orderBy = order, orderDirection = dir) }
        updateBuiltQuery()
    }

    fun setCollectionSource(source: CollectionSource) {
        _uiState.update { it.copy(collectionSource = source) }
        updateBuiltQuery()
    }

    fun toggleFilterTag(key: String) {
        val current = _uiState.value.filterTags.toMutableSet()
        if (current.contains(key)) current.remove(key) else current.add(key)
        _uiState.update { it.copy(filterTags = current) }
        updateBuiltQuery()
    }

    fun updateRarity(rarity: String){
        val current = _uiState.value.selectedRarity.toMutableList()
        if (current.contains(rarity)) current.remove(rarity) else current.add(rarity)
        _uiState.update { it.copy(selectedRarity = current) }
        updateBuiltQuery()
    }

    /** [priceCurrency] is a user preference, not a search criterion, so it survives a clear. */
    fun clearAll() {
        _uiState.value = UiState(priceCurrency = _uiState.value.priceCurrency)
        updateBuiltQuery()
    }

    /**
     * Decomposes a pre-built [AdvancedSearchQuery] back into this ViewModel's flat [UiState]
     * fields — the inverse of [rebuildQuery] — so the sheet opens ALREADY SHOWING the caller's
     * currently-applied filters as real, checked UI state, never a raw string dumped anywhere.
     *
     * Called on EVERY open of `AdvancedSearchSheet` with whatever that caller has applied, an
     * EMPTY query included: this instance is resolved with `koinViewModel()` from a fixed-position
     * overlay, so one instance serves every open within a navigation destination. Seeding from a
     * fresh [UiState] each time (preserving only `priceCurrency`, seeded from user prefs at init
     * and unrelated to any criterion) is what stops a criterion from a previous, unrelated open
     * staying live and invisibly ANDing itself onto the next search.
     *
     * [SearchCriterion.Loyalty], [SearchCriterion.Language], [SearchCriterion.Artist] and
     * [SearchCriterion.FlavorText] have no backing [UiState] field and are no-ops here — dead
     * branches in PRACTICE ([rebuildQuery] never emits them, and no
     * [SectionSearchQuery][com.mmg.manahub.feature.decks.domain.engine.SectionSearchQuery]
     * translation produces them either), kept only because [SearchCriterion] is a closed sealed
     * class and this `when` must stay exhaustive.
     */
    fun seedFrom(query: AdvancedSearchQuery) {
        val previous = _uiState.value
        var next = UiState(priceCurrency = previous.priceCurrency)
        query.criteria.forEach { criterion ->
            next = when (criterion) {
                is SearchCriterion.Name -> next.copy(nameValue = criterion.value, nameExact = criterion.exact)
                is SearchCriterion.OracleText -> next.copy(oracleText = criterion.value)
                is SearchCriterion.CardType -> next.copy(
                    cardType = criterion.types,
                    cardTypeMatchAll = criterion.matchAll,
                    cardTypeExclude = criterion.exclude,
                )
                is SearchCriterion.CardFunction -> next.copy(
                    cardFunction = criterion.functions,
                    cardFunctionMatchAll = criterion.matchAll,
                )
                is SearchCriterion.Colors -> next.copy(
                    selectedColors = criterion.colors,
                    colorMode = criterion.mode,
                    useColorIdentity = false,
                )
                is SearchCriterion.ColorIdentity -> next.copy(
                    selectedColors = criterion.colors,
                    colorMode = criterion.mode,
                    useColorIdentity = true,
                )
                is SearchCriterion.ManaCost -> next.copy(manaCostValue = criterion.value.toString(), manaCostOp = criterion.operator)
                is SearchCriterion.Rarity -> next.copy(selectedRarity = criterion.rarity)
                // A MagicSet cannot be rebuilt from its code alone, so the criterion is matched
                // against the sets already held: rebuildQuery is the only producer of a CardSet
                // criterion, so whatever it names was picked in this instance and is still there.
                is SearchCriterion.CardSet -> next.copy(
                    selectedSets = previous.selectedSets.filter { it.code in criterion.setCodes }.toSet()
                )
                is SearchCriterion.Power -> next.copy(powerValue = criterion.value.toString(), powerOp = criterion.operator)
                is SearchCriterion.Toughness -> next.copy(toughnessValue = criterion.value.toString(), toughnessOp = criterion.operator)
                is SearchCriterion.Loyalty -> next
                is SearchCriterion.Price -> next.copy(priceMax = criterion.value.toString(), priceCurrency = criterion.currency)
                is SearchCriterion.Format -> next.copy(selectedFormat = criterion.format, formatLegal = criterion.legal)
                is SearchCriterion.Language -> next
                is SearchCriterion.Artist -> next
                is SearchCriterion.FlavorText -> next
                is SearchCriterion.ManaProduction -> next.copy(manaProduction = criterion)
                is SearchCriterion.OracleTerms -> next.copy(oracleTerms = criterion)
                is SearchCriterion.CollectionStatus -> next.copy(collectionSource = criterion.source)
                is SearchCriterion.HasTag -> next.copy(filterTags = criterion.keys.toSet())
            }
        }
        // Edge-case QA fix (LOW, 2026-09-06): seedFrom used to only walk query.criteria, silently
        // dropping AdvancedSearchQuery.orderBy/.direction -- unlike rebuildQuery (the inverse
        // function), which does write them. Currently harmless (the only live caller,
        // SectionSearchQuery.toAdvancedQuery, never sets a non-default order) but a latent trap for
        // a future caller that does; seeding them here keeps seedFrom a true inverse of rebuildQuery.
        next = next.copy(orderBy = query.orderBy, orderDirection = query.direction)
        _uiState.value = next
        updateBuiltQuery()
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
