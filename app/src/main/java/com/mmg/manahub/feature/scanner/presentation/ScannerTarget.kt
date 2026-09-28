package com.mmg.manahub.feature.scanner.presentation
// COMMENTS_REVIEWED: 2026-09-16

import androidx.lifecycle.SavedStateHandle

sealed interface ScannerTarget {
    data object Collection : ScannerTarget
    data class Deck(val deckId: String) : ScannerTarget
    data object Invalid : ScannerTarget

    companion object {
        const val DECK_ID_ARGUMENT = "deckId"

        fun from(savedStateHandle: SavedStateHandle): ScannerTarget {
            if (!savedStateHandle.contains(DECK_ID_ARGUMENT)) return Collection
            return savedStateHandle.get<String>(DECK_ID_ARGUMENT)
                ?.takeIf { it.isNotBlank() }
                ?.let(::Deck)
                ?: Invalid
        }
    }
}
