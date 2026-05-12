package com.mmg.manahub.feature.synergy

import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.core.domain.model.UserCardWithCard

data class SynergyUiState(
    val isLoading:         Boolean                  = true,
    val error:             String?                  = null,
    val selectedFormat:    DeckFormat               = DeckFormat.CASUAL,
    val suggestedDecks:    List<DeckSuggestion>     = emptyList(),
    val synergyGroups:     List<SynergyGroup>       = emptyList(),
    val collectionCards:   List<UserCardWithCard>   = emptyList(),
)

enum class DeckFormat { STANDARD, PIONEER, MODERN, LEGACY, COMMANDER, PAUPER, CASUAL }

data class DeckSuggestion(
    val name:        String,
    val format:      DeckFormat,
    val colors:      List<MtgColor>,
    val cards:       List<UserCardWithCard>,
    val coverCardId: String?,
    val synergyScore: Int,   // 0-100
)

data class SynergyGroup(
    val label:       String,       // e.g. "Flying tribal", "Draw engine"
    val cards:       List<UserCardWithCard>,
    val description: String,
)
