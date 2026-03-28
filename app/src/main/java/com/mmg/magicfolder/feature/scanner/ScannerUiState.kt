package com.mmg.magicfolder.feature.scanner

import com.mmg.magicfolder.core.domain.model.Card

data class ScannerUiState(
    val isScanning:        Boolean = true,
    val detectedName:      String? = null,
    val isSearching:       Boolean = false,
    val foundCard:         Card?   = null,
    val error:             String? = null,
    val addedSuccessfully: Boolean = false,
    val showConfirmSheet:  Boolean = false,
)
