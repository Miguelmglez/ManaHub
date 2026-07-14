package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.remote.trades.TradeSuggestionsRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.TradeSuggestionDto
import com.mmg.manahub.core.model.TradeSuggestion
import com.mmg.manahub.core.domain.repository.TradeSuggestionsRepository

/**
 * Platform-agnostic [TradeSuggestionsRepository] implementation that delegates to the
 * remote data source. Moved from `:app` feature/trades during the KMP migration.
 */
class TradeSuggestionsRepositoryImpl(
    private val remote: TradeSuggestionsRemoteDataSource,
) : TradeSuggestionsRepository {

    override suspend fun getSuggestions(): Result<List<TradeSuggestion>> =
        remote.getSuggestions().map { dtos -> dtos.map { it.toDomain() } }

    override suspend fun getSuggestionsForCard(cardId: String): Result<List<TradeSuggestion>> =
        remote.getSuggestionsForCard(cardId).map { dtos -> dtos.map { it.toDomain() } }

    override suspend fun refreshSuggestions(): Result<Unit> =
        remote.refreshSuggestions()

    private fun TradeSuggestionDto.toDomain() = TradeSuggestion(
        wishingUserId = wishingUserId,
        offeringUserId = offeringUserId,
        cardId = cardId,
        matchAnyVariant = matchAnyVariant,
        userCardId = userCardId,
        offerFoil = offerFoil,
        offerCondition = offerCondition,
        offerLanguage = offerLanguage,
        suggestionType = suggestionType,
    )
}
