package com.mmg.manahub.core.model

data class FriendMatchHistory(
    val myWins: Int,
    val opponentWins: Int,
    val totalGames: Int,
    val lastPlayedAt: Long,
)
