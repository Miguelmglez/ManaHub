package com.mmg.manahub.core.online.domain.model

data class OnlineParticipant(
    val id: String,
    val sessionId: String,
    val slotIndex: Int,
    val userId: String?,
    val displayName: String,
    val themeKey: String?,
    val isHost: Boolean,
    val isReady: Boolean,
    val status: ParticipantStatus,
    val lastSeenAt: String,
)
