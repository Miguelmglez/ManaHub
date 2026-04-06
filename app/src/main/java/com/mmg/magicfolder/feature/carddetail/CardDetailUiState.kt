package com.mmg.magicfolder.feature.carddetail

import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.UserCard

data class CardDetailUiState(
    val card:          Card?          = null,
    val userCards:     List<UserCard> = emptyList(),
    val isLoading:     Boolean        = true,
    val error:         String?        = null,
    val isStale:       Boolean        = false,
    // Dialog / sheet state
    val showAddSheet:      Boolean   = false,
    val showTagPicker:     Boolean   = false,
    val cardToDelete:      UserCard? = null,
)
