package com.mmg.manahub.core.online.domain.model

data class OnlineSession(
    val id: String,
    val code: String,
    val hostUserId: String,
    val gameMode: String,
    val playerCount: Int,
    val layoutKey: String?,
    val status: OnlineSessionStatus,
    val tournamentId: String?,
    val tournamentMatchId: String?,
    val createdAt: String,
    val startedAt: String?,
    val finishedAt: String?,
    val lastActivityAt: String,
)
