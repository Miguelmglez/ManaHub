package com.mmg.manahub.core.online.data.remote.dto

import com.mmg.manahub.core.online.domain.model.SessionSnapshot
import com.mmg.manahub.core.online.domain.model.SessionState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionSnapshotDto(
    @SerialName("session")       val session: OnlineSessionDto,
    @SerialName("session_state") val sessionState: SessionStateDto? = null,
    @SerialName("player_states") val playerStates: List<SessionPlayerStateDto>,
    @SerialName("participants")  val participants: List<SessionParticipantDto>,
) {
    fun toDomain() = SessionSnapshot(
        session      = session.toDomain(),
        // Null while the session is still in the lobby: "LOBBY" is not a GamePhase, and
        // synthesising one forced every caller through getOrDefault(UNTAP)
        sessionState = sessionState?.toDomain(),
        playerStates = playerStates.map { it.toDomain() },
        participants = participants.map { it.toDomain() },
    )
}
