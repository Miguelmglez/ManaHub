package com.mmg.manahub.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.usecase.card.SearchCardUseCase
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.util.AnalyticsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val searchCards:     SearchCardsUseCase,
    private val searchCard:      SearchCardUseCase,
    private val addToCollection: AddCardToCollectionUseCase,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Called by CardNameAnalyzer when a card name is detected via OCR.
     * Ignored when [ScannerUiState.isOcrEnabled] is false.
     */
    fun onCardNameDetected(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        if (!_uiState.value.isOcrEnabled) return
        val state = _uiState.value
        if (state.foundCard != null || state.showConfirmSheet) return
        if (trimmed == state.detectedName) return

        _uiState.update { it.copy(detectedName = trimmed, isSearching = true, error = null) }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(200) // let OCR stabilize before hitting network
            try {
                // First: full-text search for the detected name
                when (val result = searchCards(trimmed)) {
                    is DataResult.Success -> {
                        val cards = result.data
                        if (cards.isNotEmpty()) {
                            _uiState.update { it.copy(
                                foundCard        = cards.first(),
                                isSearching      = false,
                                showConfirmSheet = true,
                            )}
                            return@launch
                        }
                    }
                    is DataResult.Error -> { /* fall through to exact search */ }
                }

                // Fallback: exact name lookup via SearchCardUseCase
                when (val result = searchCard(trimmed)) {
                    is DataResult.Success -> {
                        _uiState.update { it.copy(
                            foundCard        = result.data,
                            isSearching      = false,
                            showConfirmSheet = true,
                        )}
                    }
                    is DataResult.Error -> {
                        _uiState.update { it.copy(
                            isSearching = false,
                            error       = "Card not found: \"$trimmed\"",
                        )}
                        delay(2_000)
                        _uiState.update { it.copy(detectedName = null, error = null) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false, error = "Connection error") }
                delay(2_000)
                _uiState.update { it.copy(detectedName = null, error = null) }
            }
        }
    }

    fun onConfirmAdd(
        scryfallId: String,
        isFoil:     Boolean,
        condition:  String,
        language:   String,
        quantity:   Int,
    ) {
        viewModelScope.launch {
            when (val result = addToCollection(
                scryfallId = scryfallId,
                isFoil     = isFoil,
                condition  = condition,
                language   = language,
            )) {
                is DataResult.Success -> _uiState.update {
                    analyticsHelper.logEvent("scanner_add_card", mapOf("card_id" to scryfallId))
                    it.copy(
                        showConfirmSheet  = false,
                        foundCard         = null,
                        addedSuccessfully = true,
                        isScanning        = true,
                        detectedName      = null,
                        // Disable OCR after adding to prevent immediately re-scanning the same card
                        isOcrEnabled      = false,
                    )
                }
                is DataResult.Error -> _uiState.update {
                    analyticsHelper.logEvent("error_scanner_add_card", mapOf("card_id" to scryfallId))
                    it.copy(error = result.message, showConfirmSheet = false)
                }
            }
        }
    }

    /**
     * Resets scanner state after dismiss, but preserves the flash torch preference.
     */
    fun onDismissConfirmSheet() {
        _uiState.update { ScannerUiState(isFlashOn = it.isFlashOn) }
    }

    fun onToggleFlash() {
        _uiState.update { it.copy(isFlashOn = !it.isFlashOn) }
    }

    /** Toggles OCR on/off — also wired to tap-to-focus gesture on the preview. */
    fun onToggleOcr() {
        _uiState.update { it.copy(isOcrEnabled = !it.isOcrEnabled) }
    }

    fun onSuccessDismissed() = _uiState.update { it.copy(addedSuccessfully = false) }
    fun onErrorDismissed()   = _uiState.update { it.copy(error = null) }
}
