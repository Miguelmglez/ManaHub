package com.mmg.manahub.core.model

sealed class MatchResult {
    data class Victory(val winnerId: Long) : MatchResult()
    object Draw : MatchResult()
    data class Bye(val recipientId: Long) : MatchResult()
}
