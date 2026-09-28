package com.mmg.manahub.core.online.domain.model

data class SessionSnapshot(
    val session: OnlineSession,
    /** Null until the game starts and a `session_state` row exists. */
    val sessionState: SessionState?,
    val playerStates: List<SessionPlayerState>,
    val participants: List<OnlineParticipant>,
)
