package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.TradeSuggestion

interface TradeSuggestionsRepository {
    suspend fun getSuggestions(): Result<List<TradeSuggestion>>
    suspend fun getSuggestionsForCard(cardId: String): Result<List<TradeSuggestion>>
    suspend fun refreshSuggestions(): Result<Unit>
}
