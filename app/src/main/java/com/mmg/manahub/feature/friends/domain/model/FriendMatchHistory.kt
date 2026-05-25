package com.mmg.manahub.feature.friends.domain.model

data class FriendMatchHistory(
    val myWins: Int,
    val opponentWins: Int,
    val totalGames: Int,
    val lastPlayedAt: Long,
)
