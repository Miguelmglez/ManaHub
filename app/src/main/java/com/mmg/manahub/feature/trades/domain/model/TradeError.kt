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
 * Returns a user-readable message for a [Throwable].
 * Typed [TradeError] subclasses return a descriptive string; all other exceptions
 * fall back to the raw message (which may be null for truly unknown failures).
 */
fun Throwable.toUserFacingMessage(): String? = when (this) {
    is TradeError.CardAlreadyLocked          -> "Some cards are already locked in another trade"
    is TradeError.ProposalVersionMismatch    -> "The proposal was modified by the other party. Please refresh."
    is TradeError.NotFriends                 -> "You must be friends with this user to trade"
    is TradeError.InvalidStateTransition     -> "This action is no longer valid for the current trade state"
    is TradeError.InventoryGone              -> "One or more cards in this trade are no longer available"
    is TradeError.CannotAcceptReviewCollection -> "You cannot accept a proposal that only includes a collection review"
    is TradeError.InitialAsymmetryNotAllowed -> "Both sides of the trade must include at least one item or a collection review"
    is TradeError.ReviewCollectionSameDirection -> "Collection review must go in opposite directions"
    is TradeError.Unauthorized               -> "You are not authorized to perform this action"
    is TradeError.Unknown                    -> message
    else                                     -> message
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
