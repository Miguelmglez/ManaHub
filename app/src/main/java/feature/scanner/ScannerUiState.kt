package feature.scanner


import core.domain.model.Card

data class ScannerUiState(
    val isScanning:       Boolean = true,
    val scannedCard:      Card?   = null,
    val isLoadingCard:    Boolean = false,
    val error:            String? = null,
    val addedSuccessfully: Boolean = false,
    // Sheet shown after scan to confirm add
    val showConfirmSheet: Boolean = false,
    val pendingScryfallId: String? = null,
)