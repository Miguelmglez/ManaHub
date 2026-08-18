package com.mmg.manahub.feature.massiveadd.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MassiveAddCardViewModel (
    private val searchCards:        SearchCardsUseCase,
    private val buildScryfallQuery: BuildScryfallQueryUseCase,

    private val addToCollection: AddCardToCollectionUseCase,
    private val analyticsHelper: AnalyticsHelper,

    private val addToWishlistUseCase: AddToWishlistUseCase,
    private val wishlistRepo: WishlistRepository,
    private val userCardRepository: UserCardRepository
): ViewModel() {
    private var _uiState = MutableStateFlow<MassiveAddCardUiState> (MassiveAddCardUiState())
    val uiState: StateFlow<MassiveAddCardUiState> = _uiState.asStateFlow()

    data class MassiveAddCardUiState(
        val hasPreloadedItems: Boolean = false,
        val loadedCards: List<Card> = emptyList(),
        val isLoading : Boolean = true,
        val selectedSet: MagicSet? = null,
        val showSetSelectionSheet: Boolean = false,
        val showLanguagePicker: Boolean = false,
        val showCardStatusPicker: Boolean = false,
        val defaultLanguage:String = "EN",
        val defaultCardState:String = "NM",
        val showAsGrid:Boolean = true,
        val selectedCardsList: List<MassiveCard> = emptyList(),
        val processingData:Boolean = false,
        val hasMore:Boolean = false,
        val currentPage:Int = 1,
        val isLoadingMore:Boolean = false,
        val error:String = ""
    )

    data class MassiveCard(
        val card: Card,
        val quantity: Int,
        val timestamp: Long,
    )

    fun preLoadItems(cards:List<Card>){
        _uiState.update { it.copy(hasPreloadedItems = true, loadedCards = cards, isLoading = false ) }

    }
    fun loadSet(){
        _uiState.update {
                        it.copy(
                            isLoading = true,
                            error = "",
                            isLoadingMore = false,
                            hasMore = false,
                            currentPage = 1,
                            // We don't clear results here to keep stale results visible (F-08)
                        )
                    }
        viewModelScope.launch {
            val query = buildScryfallQuery(createQueryFromSelectedSet())
            when (val result = searchCards(query, page = 1)) {
                is DataResult.Success -> {
                    _uiState.update {
                        it.copy(
                            loadedCards = result.data.cards,
                            isLoading = false,
                            error = "",
                            hasMore = result.data.hasMore,
                            currentPage = 1
                        )
                    }
                }
                is DataResult.Error -> _uiState.update {
                    it.copy(error = result.message, isLoading = false)
                }
            }
        }
    }

    fun updateSelectedSet(magicSet: MagicSet){
        _uiState.update { it.copy(selectedSet = magicSet, isLoading = true) }
        loadSet()
    }
    fun removeSelectedSet(){
        _uiState.update { it.copy(selectedSet = null) }
    }
    fun showSetSelectionSheet(show:Boolean){
        _uiState.update { it.copy(showSetSelectionSheet = show) }
    }

    fun showLanguagePicker(show:Boolean){
        _uiState.update { it.copy(showLanguagePicker = show) }
    }

    fun showCardStatusPicker(show:Boolean){
        _uiState.update { it.copy(showCardStatusPicker = show) }
    }

    fun updateDefaultLanguage(language:String){
        _uiState.update { it.copy(defaultLanguage = language) }
    }

    fun updateDefaultCardState(cardState:String){
        _uiState.update { it.copy(defaultCardState = cardState) }
    }

    fun updateLayout(){
        _uiState.update { it.copy(showAsGrid = !it.showAsGrid) }
    }

    fun addCardOnList(card: Card) {
    _uiState.update { currentState ->
        val existingIndex = currentState.selectedCardsList.indexOfFirst { entry->
            entry.card.scryfallId == card.scryfallId
        }
        val updatedCards =if (existingIndex >= 0){
            currentState.selectedCardsList.toMutableList().also {
                it[existingIndex] = it[existingIndex].copy(
                    quantity = it[existingIndex].quantity + 1,
                )
            }
        } else {
            currentState.selectedCardsList + MassiveCard(
                card = card,
                1,
                System.currentTimeMillis())
            }
        currentState.copy(selectedCardsList = updatedCards)
    }
}


    fun removeCardFromList(card:Card){
        _uiState.update { currentState ->
            val existingIndex = currentState.selectedCardsList.indexOfFirst { entry->
                entry.card.scryfallId == card.scryfallId
            }
            val updatedCards = if (existingIndex >= 0){
                currentState.selectedCardsList.toMutableList().also {
                    if (it[existingIndex].quantity == 1){
                        currentState.selectedCardsList - it[existingIndex]
                    } else {
                    it[existingIndex] = it[existingIndex].copy(
                        quantity = it[existingIndex].quantity - 1,
                    )
                }}
            } else {
                uiState.value.selectedCardsList
            }
            currentState.copy(selectedCardsList = updatedCards)
        }
    }


    fun addAllToCollection(){
        val cards = _uiState.value.selectedCardsList
        if (cards.isEmpty()) return
        _uiState.update {
                it.copy(processingData =true)
        }
        viewModelScope.launch {
            for (card in cards){
                addToCollection(card.card.scryfallId,
                    isFoil = false,
                    condition = _uiState.value.defaultCardState,
                    language = _uiState.value.defaultLanguage,
                    quantity = card.quantity)
                delay(100.milliseconds)
            }
            analyticsHelper.logEvent(
                "multiple_add_all",
                mapOf("count" to cards.size.toString()),
            )
            _uiState.update {
                it.copy(selectedCardsList = emptyList(), processingData = false)
            }
            /*_uiState.update {
               //Show toast message
              //  it.copy(toastMessage = context.getString(R.string.scanner_toast_added_all_to_collection, cards.size))
            }*/
        }
    }

    fun addAllToWishlist(){
        val cards = _uiState.value.selectedCardsList
        if (cards.isEmpty()) return
        _uiState.update {
            it.copy(processingData =true)
        }
        /*viewModelScope.launch {
            for (card in cards){
                addToWishlistUseCase(card.card.scryfallId,
                    isFoil = false,
                    condition = _uiState.value.defaultCardState,
                    language = _uiState.value.defaultLanguage,
                    quantity = card.quantity)
                delay(100.milliseconds)
            }
            analyticsHelper.logEvent(
                "multiple_add_all",
                mapOf("count" to cards.size.toString()),
            )
            _uiState.update {
                it.copy(selectedCardsList = emptyList(), processingData = false)
            }
            *//*_uiState.update {
               //Show toast message
              //  it.copy(toastMessage = context.getString(R.string.scanner_toast_added_all_to_collection, cards.size))
            }*//*
        }*/
        _uiState.update {
            it.copy(selectedCardsList = emptyList(), processingData = false)
        }
    }


    fun getCopiesFromCard(card:Card):Int{
        val existingIndex = _uiState.value.selectedCardsList.indexOfFirst{ entry->
            entry.card.scryfallId == card.scryfallId
        }
        return if (existingIndex>=0){
             _uiState.value.selectedCardsList[existingIndex].quantity
        } else {
            0
        }
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        val query = buildScryfallQuery(createQueryFromSelectedSet())

        if (!currentState.hasMore || currentState.isLoadingMore || currentState.processingData) return
        val nextPage = currentState.currentPage + 1
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            when (val result = searchCards(query, nextPage)) {
                is DataResult.Success -> {}

                is DataResult.Error -> {
                    _uiState.update {
                        it.copy(isLoadingMore = false, error = result.message)
                    }
                }
            }
        }

    }

    private fun createQueryFromSelectedSet():AdvancedSearchQuery{
        val criterionList : List <SearchCriterion> = listOf(SearchCriterion.CardSet(setOf(_uiState.value.selectedSet!!.code)))
        return AdvancedSearchQuery(criteria = criterionList)
    }
    }

