package com.mmg.manahub.core.online.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActiveSessionDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("code") val code: String,
    @SerialName("status") val status: String,
    @SerialName("game_mode") val gameMode: String,
    @SerialName("player_count") val playerCount: Int,
)
