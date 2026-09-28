package com.mmg.manahub.feature.trades.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.SharedListsRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.SharedListItem
import com.mmg.manahub.core.model.SharedListResult
import com.mmg.manahub.core.model.SharedListType
import com.mmg.manahub.core.model.isValidShareId
import com.mmg.manahub.core.util.recordSafeNonFatal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One rendered row of a shared list; [key] is unique within the list. */
data class SharedListRow(
    val key: String,
    val item: SharedListItem,
    val card: Card?,
)

/**
 * Models all possible display states for the shared-list deep link landing screen.
 */
sealed class SharedListUiState {
    /** The share ID is being resolved against the remote database. */
    object Loading : SharedListUiState()

    /** Successfully resolved list, with card metadata attached where the cache could resolve it. */
    data class Success(
        val listType: SharedListType,
        val ownerNickname: String,
        val rows: List<SharedListRow>,
    ) : SharedListUiState()

    /** The owner has revoked public sharing. */
    object Private : SharedListUiState()

    /** No list exists for this share ID. */
    object NotFound : SharedListUiState()

    /** The list could not be resolved; the screen offers a retry. */
    object Error : SharedListUiState()
}

/**
 * ViewModel for [TradesSharedListScreen].
 *
 * Reads the `shareId` nav argument, resolves it through [SharedListsRepository.resolveSharedList]
 * and warms the card cache so rows render names and images instead of raw ids.
 */
class TradesSharedListViewModel(
    savedStateHandle: SavedStateHandle,
    private val sharedListsRepository: SharedListsRepository,
    private val cardRepository: CardRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SharedListUiState>(SharedListUiState.Loading)
    val uiState: StateFlow<SharedListUiState> = _uiState.asStateFlow()

    private val shareId: String? = savedStateHandle.get<String>("shareId")?.trim()
    private var resolveJob: Job? = null

    init {
        load()
    }

    /** Re-resolves the list after an error. */
    fun retry() {
        if (resolveJob?.isActive == true) return
        load()
    }

    private fun load() {
        val id = shareId
        if (id.isNullOrBlank() || !isValidShareId(id)) {
            _uiState.value = SharedListUiState.NotFound
            return
        }
        _uiState.value = SharedListUiState.Loading
        resolveJob = viewModelScope.launch(ioDispatcher) {
            _uiState.value = sharedListsRepository.resolveSharedList(id).fold(
                onSuccess = { result ->
                    when (result) {
                        is SharedListResult.Ok -> result.toUiState()
                        SharedListResult.Private -> SharedListUiState.Private
                        SharedListResult.NotFound -> SharedListUiState.NotFound
                    }
                },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    recordSafeNonFatal("trade_shared_list_resolve_failed", e)
                    SharedListUiState.Error
                },
            )
        }
    }

    private suspend fun SharedListResult.Ok.toUiState(): SharedListUiState.Success {
        val cardIds = items.map { it.cardId }.distinct()
        val cards = try {
            // Best-effort: rows without cached metadata still render with a fallback name.
            if (cardIds.isNotEmpty()) cardRepository.warmCacheForIds(cardIds)
            cardRepository.getCardsByIds(cardIds).associateBy { it.scryfallId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordSafeNonFatal("trade_shared_list_card_lookup_failed", e)
            emptyMap()
        }
        val seen = HashMap<String, Int>()
        val rows = items
            .map { item -> SharedListRow(key = uniqueKey(item, seen), item = item, card = cards[item.cardId]) }
            .sortedBy { it.card?.name ?: "￿" }
        return SharedListUiState.Success(listType = listType, ownerNickname = ownerNickname, rows = rows)
    }

    private fun uniqueKey(item: SharedListItem, seen: MutableMap<String, Int>): String {
        val base = item.userCardId
            ?: "${item.cardId}|${item.isFoil}|${item.condition}|${item.language}|${item.matchAnyVariant}"
        val occurrence = seen.merge(base, 1, Int::plus) ?: 1
        return if (occurrence == 1) base else "$base#$occurrence"
    }
}
