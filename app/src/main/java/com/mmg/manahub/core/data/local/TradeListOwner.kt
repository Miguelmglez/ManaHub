package com.mmg.manahub.core.data.local

/** Explicit provenance for trade-list rows created while signed out. */
object TradeListOwner {
    const val GUEST = "local_guest"

    fun key(userId: String?): String = userId ?: GUEST
}
