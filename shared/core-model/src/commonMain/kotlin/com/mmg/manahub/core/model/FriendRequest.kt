package com.mmg.manahub.core.model

data class FriendRequest(
    val id: String,
    val fromUserId: String,
    val fromNickname: String,
    val fromGameTag: String,
    val fromAvatarUrl: String?,
)
