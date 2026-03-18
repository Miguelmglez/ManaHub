package feature.carddetail

import core.domain.model.Card
import core.domain.model.UserCard


data class CardDetailUiState(
    val card:         Card?     = null,
    val userCard:     UserCard? = null,
    val isLoading:    Boolean   = true,
    val error:        String?   = null,
    val isStale:      Boolean   = false,
    // Dialog state
    val showEditDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
)