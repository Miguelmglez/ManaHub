package com.mmg.manahub.core.online.domain.model

data class SessionSnapshot(
    val session: OnlineSession,
    val sessionState: SessionState,
    val playerStates: List<SessionPlayerState>,
    val participants: List<OnlineParticipant>,
)
