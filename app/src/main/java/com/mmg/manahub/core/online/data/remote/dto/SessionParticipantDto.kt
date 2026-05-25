package com.mmg.manahub.core.online.data.remote.dto

import com.mmg.manahub.core.online.domain.model.OnlineParticipant
import com.mmg.manahub.core.online.domain.model.ParticipantStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionParticipantDto(
    @SerialName("id")            val id: String,
    @SerialName("session_id")    val sessionId: String,
    @SerialName("slot_index")    val slotIndex: Int,
    @SerialName("user_id")       val userId: String? = null,
    @SerialName("display_name")  val displayName: String,
    @SerialName("theme_key")     val themeKey: String? = null,
    @SerialName("is_host")       val isHost: Boolean = false,
    @SerialName("is_ready")      val isReady: Boolean = false,
    @SerialName("status")        val status: String,
    @SerialName("last_seen_at")  val lastSeenAt: String,
) {
    fun toDomain() = OnlineParticipant(
        id          = id,
        sessionId   = sessionId,
        slotIndex   = slotIndex,
        userId      = userId,
        displayName = displayName,
        themeKey    = themeKey,
        isHost      = isHost,
        isReady     = isReady,
        status      = runCatching { ParticipantStatus.valueOf(status) }.getOrDefault(ParticipantStatus.LEFT),
        lastSeenAt  = lastSeenAt,
    )
}
