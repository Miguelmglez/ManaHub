package com.mmg.magicfolder.feature.scanner


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.core.domain.usecase.card.SearchCardUseCase
import com.mmg.magicfolder.core.domain.usecase.collection.AddCardToCollectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val searchCard: SearchCardUseCase,
    private val addToCollection: AddCardToCollectionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    // Called by CameraX BarcodeAnalyzer when a barcode is detected
    fun onBarcodeDetected(rawValue: String) {
        if (_uiState.value.isLoadingCard || _uiState.value.showConfirmSheet) return
        _uiState.update { it.copy(isScanning = false, isLoadingCard = true) }

        viewModelScope.launch {
            // Scryfall accepts multiverseId or collector number queries
            when (val result = searchCard(rawValue)) {
                is DataResult.Success<*> -> _uiState.update {
                    it.copy(
                        scannedCard       = result.data as Card?,
                        isLoadingCard     = false,
                        showConfirmSheet  = true,
                        pendingScryfallId = result.data?.scryfallId,
                    )
                }
                is DataResult.Error -> _uiState.update {
                    it.copy(error = result.message, isLoadingCard = false, isScanning = true)
                }
            }
        }
    }

    fun onConfirmAdd(
        scryfallId: String,
        isFoil:    Boolean,
        condition: String,
        language:  String,
        quantity:  Int,
    ) {
        viewModelScope.launch {
            when (val result = addToCollection(scryfallId, isFoil, condition, language, quantity)) {
                is DataResult.Success<*> -> _uiState.update {
                    it.copy(
                        showConfirmSheet   = false,
                        scannedCard        = null,
                        addedSuccessfully  = true,
                        isScanning         = true,
                        pendingScryfallId  = null,
                    )
                }
                is DataResult.Error -> _uiState.update {
                    it.copy(error = result.message, showConfirmSheet = false)
                }
            }
        }
    }

    fun onDismissConfirmSheet() {
        _uiState.update { it.copy(showConfirmSheet = false, scannedCard = null, isScanning = true) }
    }

    fun onSuccessDismissed() = _uiState.update { it.copy(addedSuccessfully = false) }
    fun onErrorDismissed()   = _uiState.update { it.copy(error = null) }
}
