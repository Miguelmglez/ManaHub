package com.mmg.manahub.feature.stats

import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.MagicSet
import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.util.PriceFormatter

enum class StatsTab { COLLECTION, GAMES, TRADES }

data class StatsUiState(
    val selectedTab:         StatsTab         = StatsTab.COLLECTION,
    val selectedColor:       MtgColor?        = null,
    val selectedSet:         MagicSet?        = null,
    val stats:               CollectionStats? = null,
    val availableSets:       List<MagicSet>   = emptyList(),
    val currency:            PreferredCurrency = if (PriceFormatter.isEuropeanLocale()) PreferredCurrency.EUR else PreferredCurrency.USD,
    val isLoading:           Boolean          = true,
    val error:               String?          = null,
    val isRefreshingPrices:  Boolean          = false,
    val refreshProgress:     Pair<Int, Int>?  = null,
    val lastRefreshedAt:     Long?            = null,
    val refreshError:        String?          = null,
    val refreshResult:       String?          = null,
    
    // Placeholder for Games and Trades data
    val gameStats:           GameStatsSummary?  = null,
    val tradeStats:          TradeStatsSummary? = null,
)

data class GameStatsSummary(
    val totalGames:      Int,
    val winRate:         Double,
    val favoriteMode:    String?,
    val favoriteDeck:    String?,
)

data class TradeStatsSummary(
    val openForTradeCount: Int,
    val wishlistCount:     Int,
    val totalTradeValue:   Double,
)
