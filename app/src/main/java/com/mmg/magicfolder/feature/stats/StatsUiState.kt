package com.mmg.magicfolder.feature.stats

import com.mmg.magicfolder.core.domain.model.CollectionStats

data class StatsUiState(
    val stats:        CollectionStats? = null,
    val currency:     Currency        = Currency.USD,
    val isLoading:    Boolean         = true,
    val error:        String?         = null,
)

enum class Currency { USD, EUR }
