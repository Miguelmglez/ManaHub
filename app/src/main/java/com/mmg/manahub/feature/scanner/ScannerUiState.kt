package com.mmg.manahub.feature.scanner

import com.mmg.manahub.core.domain.model.Card

data class ScannerUiState(
    val isScanning:        Boolean = true,
    val detectedName:      String? = null,
    val isSearching:       Boolean = false,
    val foundCard:         Card?   = null,
    val error:             String? = null,
    val addedSuccessfully: Boolean = false,
    val showConfirmSheet:  Boolean = false,
    val isFlashOn:         Boolean = false,
    /** When false, the ViewModel ignores OCR detections from the analyzer. */
    val isOcrEnabled:      Boolean = true,
)
