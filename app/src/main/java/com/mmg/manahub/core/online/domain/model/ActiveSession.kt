package com.mmg.manahub.core.online.domain.model

data class ActiveSession(
    val sessionId: String,
    val code: String,
    val status: String,
    val gameMode: String,
    val playerCount: Int,
)
