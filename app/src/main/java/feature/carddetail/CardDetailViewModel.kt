package feature.carddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.domain.model.DataResult
import core.domain.model.UserCard
import core.domain.repository.CardRepository
import core.domain.repository.UserCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    savedStateHandle:       SavedStateHandle,
    private val cardRepo: CardRepository,
    private val userCardRepo: UserCardRepository,
) : ViewModel() {

    private val scryfallId: String = checkNotNull(savedStateHandle["scryfallId"])

    private val _uiState = MutableStateFlow(CardDetailUiState())
    val uiState: StateFlow<CardDetailUiState> = _uiState.asStateFlow()

    init {
        loadCard()
        observeUserCard()
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
        // Also observe reactively so UI updates when cache is refreshed
        viewModelScope.launch {
            cardRepo.observeCard(scryfallId)
                .filterNotNull()
                .collect { card -> _uiState.update { it.copy(card = card, isStale = card.isStale) } }
        }
    }

    private fun observeUserCard() {
        viewModelScope.launch {
            userCardRepo.searchInCollection(scryfallId)
                .collect { list ->
                    // Find the entry for this specific scryfallId
                    val match = list.firstOrNull { it.card.scryfallId == scryfallId }
                    _uiState.update { it.copy(userCard = match?.userCard) }
                }
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

    fun onShowEditDialog()    = _uiState.update { it.copy(showEditDialog = true) }
    fun onDismissEditDialog() = _uiState.update { it.copy(showEditDialog = false) }
    fun onShowDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = true) }
    fun onDismissDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = false) }

    fun onDeleteCard(userCardId: Long) {
        viewModelScope.launch {
            runCatching { userCardRepo.deleteCard(userCardId) }
                .onSuccess  { _uiState.update { it.copy(showDeleteConfirm = false) } }
                .onFailure  { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
}