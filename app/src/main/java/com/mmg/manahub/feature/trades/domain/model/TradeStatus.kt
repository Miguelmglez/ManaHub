package com.mmg.manahub.feature.trades.domain.model

/** Represents every state a trade proposal can occupy in the server state machine. */
enum class TradeStatus {
    DRAFT, PROPOSED, CANCELLED, DECLINED, COUNTERED, ACCEPTED, COMPLETED, REVOKED;

    fun canTransitionTo(next: TradeStatus): Boolean = when (this) {
        DRAFT -> next in setOf(PROPOSED)
        PROPOSED -> next in setOf(CANCELLED, DECLINED, COUNTERED, ACCEPTED)
        ACCEPTED -> next in setOf(COMPLETED, REVOKED)
        else -> false
    }

    val isTerminal: Boolean get() = this in setOf(CANCELLED, DECLINED, COUNTERED, COMPLETED, REVOKED)
    val isActive: Boolean get() = !isTerminal
}
