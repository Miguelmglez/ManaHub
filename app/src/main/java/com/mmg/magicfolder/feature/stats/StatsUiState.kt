package com.mmg.magicfolder.feature.stats

import com.mmg.magicfolder.core.domain.model.CollectionStats
import com.mmg.magicfolder.core.domain.model.PreferredCurrency
import com.mmg.magicfolder.core.util.PriceFormatter

data class StatsUiState(
    val stats:               CollectionStats? = null,
    val currency:            PreferredCurrency = if (PriceFormatter.isEuropeanLocale()) PreferredCurrency.EUR else PreferredCurrency.USD,
    val isLoading:           Boolean          = true,
    val error:               String?          = null,
    val isRefreshingPrices:  Boolean          = false,
    val refreshProgress:     Pair<Int, Int>?  = null,  // current / total chunks
    val lastRefreshedAt:     Long?            = null,
    val refreshError:        String?          = null,
    val refreshResult:       String?          = null,  // success message
    val autoRefreshPrices:   Boolean          = false,
)
