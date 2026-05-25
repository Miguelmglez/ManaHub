package com.mmg.manahub.core.online.domain.model

data class SessionState(
    val sessionId: String,
    val currentPhase: String,
    val activePlayerSlot: Int,
    val turnNumber: Int,
    val phaseStops: Map<String, Boolean>,
    val lastDiceResult: Int?,
    val lastCoinResult: String?,
    val updatedAt: String,
)
