package com.mmg.manahub.core.model

/**
 * Aggregate trade-history stats computed over the local user's COMPLETED [TradeProposal]s
 * (Stats screen Phase 4, 2026-07 stats expansion).
 *
 * All value figures are ESTIMATED — resolved from each [TradeItem]'s price snapshot at the time
 * its thread was last refreshed, not a live re-price — and expressed in [currency]. Review-
 * collection placeholder items ([TradeItem.isReviewCollectionPlaceholder]) are excluded from every
 * count and the value delta since they never represent a real card transfer.
 *
 * @param completedTradesCount Number of proposals with [TradeStatus.COMPLETED] status. Exactly one
 *   proposal per counter-offer thread ([TradeProposal.rootProposalId]) can ever reach COMPLETED, so
 *   this also equals the number of completed negotiations.
 * @param cardsSent Total quantity of cards sent by the local user across all completed trades.
 * @param cardsReceived Total quantity of cards received by the local user across all completed trades.
 * @param netValueDelta Estimated value received minus value sent, in [currency]. Positive means the
 *   user came out ahead in aggregate.
 * @param topPartner Highest-volume counterparty by completed-trade count; null when no completed
 *   trades exist.
 */
data class TradeStats(
    val completedTradesCount: Int,
    val cardsSent: Int,
    val cardsReceived: Int,
    val netValueDelta: Double,
    val currency: PreferredCurrency,
    val topPartner: TradePartnerSummary?,
)

/** Completed-trade volume with one counterparty, keyed by their auth user id. */
data class TradePartnerSummary(
    val userId: String,
    /** Resolved friend nickname/game-tag, falling back to "Unknown" — never a raw user id. */
    val displayName: String,
    val completedTradesCount: Int,
)
