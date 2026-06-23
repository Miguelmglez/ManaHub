package com.mmg.manahub.core.model

data class Friend(
    val id: String,
    val userId: String,
    val nickname: String,
    val gameTag: String,
    val avatarUrl: String?,
)
