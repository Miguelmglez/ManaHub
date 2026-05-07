package com.mmg.manahub.feature.trades.domain.repository

import com.mmg.manahub.feature.trades.domain.model.TradeSuggestion

interface TradeSuggestionsRepository {
    suspend fun getSuggestions(): Result<List<TradeSuggestion>>
    suspend fun getSuggestionsForCard(cardId: String): Result<List<TradeSuggestion>>
    suspend fun refreshSuggestions(): Result<Unit>
}
