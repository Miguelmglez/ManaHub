package com.mmg.magicfolder.feature.carddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.data.local.UserPreferencesDataStore
import com.mmg.magicfolder.core.domain.model.CardTag
import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.core.domain.model.TagCategory
import com.mmg.magicfolder.core.domain.model.UserCard
import com.mmg.magicfolder.core.domain.model.UserDefinedTag
import com.mmg.magicfolder.core.domain.repository.CardRepository
import com.mmg.magicfolder.core.domain.repository.DeckRepository
import com.mmg.magicfolder.core.domain.repository.UserCardRepository
import com.mmg.magicfolder.core.domain.usecase.collection.AddCardToCollectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    savedStateHandle:            SavedStateHandle,
    private val cardRepo:        CardRepository,
    private val userCardRepo:    UserCardRepository,
    private val deckRepo:        DeckRepository,
    private val addToCollection: AddCardToCollectionUseCase,
    private val userPrefs:       UserPreferencesDataStore,
) : ViewModel() {

    private val scryfallId: String = checkNotNull(savedStateHandle["scryfallId"])

    private val _uiState = MutableStateFlow(CardDetailUiState())
    val uiState: StateFlow<CardDetailUiState> = _uiState.asStateFlow()

    init {
        loadCard()
        observeUserCards()
        observeDecks()
        viewModelScope.launch {
            userPrefs.userDefinedTagsFlow.collect { tags ->
                _uiState.update { it.copy(userDefinedTags = tags) }
            }
        }
    }

    private fun observeDecks() {
        viewModelScope.launch {
            deckRepo.observeDecksContainingCard(scryfallId)
                .catch { /* ignore — decks section is non-critical */ }
                .collect { decks -> _uiState.update { it.copy(decksContainingCard = decks) } }
        }
    }

    private fun loadCard() {
        viewModelScope.launch {
            when (val result = cardRepo.getCardById(scryfallId)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(card = result.data, isLoading = false, isStale = result.isStale)
                }
                is DataResult.Error   -> _uiState.update {
                    it.copy(error = result.message, isLoading = false)
                }
            }
        }
        viewModelScope.launch {
            cardRepo.observeCard(scryfallId)
                .filterNotNull()
                .collect { card -> _uiState.update { it.copy(card = card, isStale = card.isStale) } }
        }
    }

    private fun observeUserCards() {
        viewModelScope.launch {
            userCardRepo.observeByScryfallId(scryfallId)
                .collect { cards ->
                    // Wishlist entries are stored separately; exclude them from the
                    // collection list shown in CardDetailScreen.
                    _uiState.update { it.copy(userCards = cards.filter { c -> !c.isInWishlist }) }
                }
        }
    }

    fun onAddToCollection(
        isFoil:           Boolean,
        isAlternativeArt: Boolean,
        condition:        String,
        language:         String,
        quantity:         Int,
    ) {
        viewModelScope.launch {
            val result = addToCollection(
                scryfallId       = scryfallId,
                isFoil           = isFoil,
                isAlternativeArt = isAlternativeArt,
                condition        = condition,
                language         = language,
                quantity         = quantity,
            )
            if (result is DataResult.Error) {
                _uiState.update { it.copy(error = result.message) }
            }
            _uiState.update { it.copy(showAddSheet = false) }
        }
    }

    fun onUpdateQuantity(userCardId: Long, quantity: Int) {
        viewModelScope.launch {
            runCatching { userCardRepo.updateQuantity(userCardId, quantity) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onUpdateCondition(userCard: UserCard, condition: String) {
        viewModelScope.launch {
            runCatching { userCardRepo.updateCard(userCard.copy(condition = condition)) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onShowAddSheet()          = _uiState.update { it.copy(showAddSheet = true) }
    fun onDismissAddSheet()       = _uiState.update { it.copy(showAddSheet = false) }
    fun onShowWishlistSheet()     = _uiState.update { it.copy(showWishlistSheet = true) }
    fun onDismissWishlistSheet()  = _uiState.update { it.copy(showWishlistSheet = false) }
    fun onShowTradeSheet()        = _uiState.update { it.copy(showTradeSheet = true) }
    fun onDismissTradeSheet()     = _uiState.update { it.copy(showTradeSheet = false) }
    fun onShowTagPicker()         = _uiState.update { it.copy(showTagPicker = true) }
    fun onDismissTagPicker()      = _uiState.update { it.copy(showTagPicker = false) }
    fun onRequestDelete(uc: UserCard) = _uiState.update { it.copy(cardToDelete = uc) }
    fun onDismissDeleteConfirm()  = _uiState.update { it.copy(cardToDelete = null) }

    fun onAddToWishlist(
        isFoil:           Boolean,
        isAlternativeArt: Boolean,
        condition:        String,
        language:         String,
        quantity:         Int,
    ) {
        viewModelScope.launch {
            val wishlistCard = UserCard(
                scryfallId       = scryfallId,
                isFoil           = isFoil,
                isAlternativeArt = isAlternativeArt,
                condition        = condition,
                language         = language,
                quantity         = quantity,
                isInWishlist     = true,
            )
            runCatching { userCardRepo.addOrIncrement(wishlistCard) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            _uiState.update { it.copy(showWishlistSheet = false) }
        }
    }

    fun onConfirmTradeSelection(selections: Map<Long, Boolean>) {
        val userCards = _uiState.value.userCards
        viewModelScope.launch {
            selections.forEach { (id, isForTrade) ->
                val card = userCards.find { it.id == id } ?: return@forEach
                if (card.isForTrade != isForTrade) {
                    runCatching { userCardRepo.updateCard(card.copy(isForTrade = isForTrade)) }
                        .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
                }
            }
            _uiState.update { it.copy(showTradeSheet = false) }
        }
    }

    fun onAddTag(tag: CardTag) {
        val current = _uiState.value.card?.tags ?: return
        if (tag in current) return
        viewModelScope.launch {
            runCatching { cardRepo.updateCardTags(scryfallId, current + tag) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onRemoveTag(tag: CardTag) {
        val current = _uiState.value.card?.tags ?: return
        viewModelScope.launch {
            runCatching { cardRepo.updateCardTags(scryfallId, current - tag) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onAddUserTag(tag: CardTag) {
        val current = _uiState.value.card?.userTags ?: return
        if (tag in current) return
        viewModelScope.launch {
            runCatching { cardRepo.updateUserTags(scryfallId, current + tag) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onRemoveUserTag(tag: CardTag) {
        val current = _uiState.value.card?.userTags ?: return
        viewModelScope.launch {
            runCatching { cardRepo.updateUserTags(scryfallId, current - tag) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    /**
     * Creates a new user-defined tag, saves it globally (so it appears in all
     * future TagPickerSheet instances), and adds it to this card's [userTags].
     */
    fun onSaveAndAddCustomTag(label: String, categoryKey: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        val key = trimmed.lowercase()
            .replace(' ', '_')
            .replace(Regex("[^a-z0-9_]"), "")
            .take(50)
        if (key.isEmpty()) return
        val userDefinedTag = UserDefinedTag(key = key, label = trimmed, categoryKey = categoryKey)
        viewModelScope.launch {
            runCatching {
                userPrefs.saveUserDefinedTag(userDefinedTag)
                cardRepo.updateUserTags(
                    scryfallId,
                    ((_uiState.value.card?.userTags ?: emptyList()) + CardTag(key, TagCategory.CUSTOM)).distinct()
                )
            }.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onConfirmSuggestedTag(tag: CardTag) {
        viewModelScope.launch {
            runCatching { cardRepo.confirmSuggestedTag(scryfallId, tag) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onDismissSuggestedTag(tag: CardTag) {
        viewModelScope.launch {
            runCatching { cardRepo.dismissSuggestedTag(scryfallId, tag) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onDeleteCard(userCardId: Long) {
        viewModelScope.launch {
            runCatching { userCardRepo.deleteCard(userCardId) }
                .onSuccess  { _uiState.update { it.copy(cardToDelete = null) } }
                .onFailure  { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
}
