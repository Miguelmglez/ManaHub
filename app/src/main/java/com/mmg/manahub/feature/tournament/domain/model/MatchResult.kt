package com.mmg.manahub.feature.tournament.domain.model

sealed class MatchResult {
    data class Victory(val winnerId: Long) : MatchResult()
    object Draw : MatchResult()
    data class Bye(val recipientId: Long) : MatchResult()
}
