package com.mmg.manahub.core.online.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JoinSessionResponseDto(
    @SerialName("session_id")  val sessionId: String,
    @SerialName("slot_index")  val slotIndex: Int,
)
