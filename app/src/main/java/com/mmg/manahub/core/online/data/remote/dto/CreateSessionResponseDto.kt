package com.mmg.manahub.core.online.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionResponseDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("code")       val code: String,
)
