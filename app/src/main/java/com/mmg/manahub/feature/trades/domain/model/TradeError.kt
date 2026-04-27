package com.mmg.manahub.feature.trades.domain.model

/** Typed domain errors raised by the Supabase trade state machine. */
sealed class TradeError : Exception() {
    data class CardAlreadyLocked(val cardIds: List<String>) : TradeError()
    object ProposalVersionMismatch : TradeError()
    object NotFriends : TradeError()
    object InvalidStateTransition : TradeError()
    object InventoryGone : TradeError()
    object CannotAcceptReviewCollection : TradeError()
    object InitialAsymmetryNotAllowed : TradeError()
    object ReviewCollectionSameDirection : TradeError()
    object Unauthorized : TradeError()
    data class Unknown(override val message: String?) : TradeError()
}

/**
 * Parses a raw [PostgrestException] message string into a typed [TradeError].
 * The Supabase state-machine RPCs raise errors in the format "ERROR_CODE: detail".
 */
fun parseTradeError(message: String?): TradeError {
    if (message == null) return TradeError.Unknown(null)
    return when {
        message.startsWith("CARD_ALREADY_LOCKED") -> {
            val cards = message.substringAfter(":").trim().split(",").filter { it.isNotBlank() }
            TradeError.CardAlreadyLocked(cards)
        }
        message.startsWith("PROPOSAL_VERSION_MISMATCH") -> TradeError.ProposalVersionMismatch
        message.startsWith("NOT_FRIENDS") -> TradeError.NotFriends
        message.startsWith("INVALID_STATE") -> TradeError.InvalidStateTransition
        message.startsWith("INVENTORY_GONE") -> TradeError.InventoryGone
        message.startsWith("CANNOT_ACCEPT_REVIEW_COLLECTION") -> TradeError.CannotAcceptReviewCollection
        message.startsWith("INITIAL_ASYMMETRY") -> TradeError.InitialAsymmetryNotAllowed
        message.startsWith("REVIEW_COLLECTION_SAME_DIRECTION") -> TradeError.ReviewCollectionSameDirection
        message.startsWith("UNAUTHORIZED") -> TradeError.Unauthorized
        else -> TradeError.Unknown(message)
    }
}
