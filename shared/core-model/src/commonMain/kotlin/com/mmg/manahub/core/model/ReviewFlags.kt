package com.mmg.manahub.core.model

/** Flags indicating whether each side of a trade includes a collection review. */
data class ReviewFlags(val fromProposer: Boolean, val fromReceiver: Boolean)
