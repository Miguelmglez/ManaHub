package com.mmg.manahub.feature.friends.domain.model

data class Friend(
    val id: String,
    val userId: String,
    val nickname: String,
    val gameTag: String,
    val avatarUrl: String?,
)
