package com.mmg.manahub.feature.friends.domain.model

data class OutgoingFriendRequest(
    val id: String,
    val toUserId: String,
    val toNickname: String,
    val toGameTag: String,
    val toAvatarUrl: String?,
)
