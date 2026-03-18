package feature.stats


import core.domain.model.CollectionStats

data class StatsUiState(
    val stats:     CollectionStats? = null,
    val isLoading: Boolean          = true,
    val error:     String?          = null,
    val currency:  Currency         = Currency.USD,
)

enum class Currency { USD, EUR }